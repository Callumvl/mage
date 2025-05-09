package org.mage.test.load;

import mage.constants.PlayerAction;
import mage.interfaces.callback.CallbackClient;
import mage.interfaces.callback.ClientCallback;
import mage.remote.Session;
import mage.view.*;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.junit.Assert;

import java.util.List;
import java.util.UUID;

/**
 * @author JayDi85
 */
public class LoadCallbackClient implements CallbackClient {

    //private static final Logger log = Logger.getLogger(LoadCallbackClient.class);
    private static final Logger log = Logger.getLogger("Load call");

    private Session session;
    private UUID gameId;
    private UUID playerId;
    private boolean gameOver;
    private String gameResult = "unknown";
    private boolean needToConcede = false; // will concede on first priority
    private boolean joinGameChat = false; // process CHATMESSAGE

    private volatile int controlCount;

    private GameView gameView;

    private final String logsPrefix;
    private final Boolean showLogsAsHtml; // original game logs in HTML, but it can be converted to txt for more readable console
    private String globalProgress = ""; // example: progress 33% [20.cd, 21.__, 17.__], AI game #09: ---

    public LoadCallbackClient(boolean joinGameChat, String logsPrefix, Boolean showLogsAsHtml) {
        this.joinGameChat = joinGameChat;
        this.logsPrefix = logsPrefix;
        this.showLogsAsHtml = showLogsAsHtml;
    }

    protected void updateGlobalProgress(String globalProgress) {
        this.globalProgress = globalProgress;
    }

    @Override
    public void onNewConnection() {
        // nothing to do, only one time connection for LoadClient
    }

    @Override
    public void onCallback(ClientCallback callback) {
        callback.decompressData();
        controlCount = 0;

        // ignore bloated logs
        switch (callback.getMethod()) {
            case CHATMESSAGE:
            case GAME_UPDATE_AND_INFORM:
            case GAME_UPDATE:
                break;
            default:
                log.info(getLogStartInfo() + "callback: " + callback.getMethod());
        }

        switch (callback.getMethod()) {

            case GAME_INIT:
                this.gameId = callback.getObjectId();
                if (joinGameChat) {
                    session.joinChat(session.getGameChatId(gameId).get());
                }
                break;

            case GAME_UPDATE:
                GameView newGameView = (GameView) callback.getData();
                Assert.assertNotNull("game update event must return game view object", newGameView);
                this.gameView = newGameView;
                break;

            case CHATMESSAGE: {
                ChatMessage message = (ChatMessage) callback.getData();
                String mes = this.showLogsAsHtml ? message.getMessage() : Jsoup.parse(message.getMessage()).text();
                log.info(getLogStartInfo() + "chat message" + (message.getTurnInfo() == null ? "" : " at " + message.getTurnInfo()) + ": " + mes);
                break;
            }

            case START_GAME: {
                TableClientMessage message = (TableClientMessage) callback.getData();
                log.info(getLogStartInfo() + "game started");
                gameId = message.getGameId();
                playerId = message.getPlayerId();
                session.joinGame(message.getGameId());
                startControlThread();
                break;
            }

            case GAME_UPDATE_AND_INFORM:
            case GAME_INFORM_PERSONAL: {
                GameClientMessage message = (GameClientMessage) callback.getData();
                this.gameView = message.getGameView();
                // ignore play priority log
                break;
            }

            case SHOW_USERMESSAGE: {
                List<String> messageData = (List<String>) callback.getData();
                log.info(getLogStartInfo() + "warning message: " + String.join(" - ", messageData));
                break;
            }

            case GAME_TARGET: {
                GameClientMessage message = (GameClientMessage) callback.getData();
                this.gameView = message.getGameView();
                log.info(getLogStartInfo() + " target: " + message.getMessage());
                if (message.getMessage().startsWith("Select a starting player")) {
                    session.sendPlayerUUID(gameId, playerId);
                    return;
                } else if (message.getMessage().startsWith("Select a card to discard")) {
                    log.info(getLogStartInfo() + "hand size: " + gameView.getMyHand().size());
                    SimpleCardView card = gameView.getMyHand().values().iterator().next();
                    session.sendPlayerUUID(gameId, card.getId());
                    return;
                } else {
                    log.error(getLogStartInfo() + "unknown GAME_TARGET message: " + message.toString());
                    return;
                }
            }

            case GAME_ASK: {
                GameClientMessage message = (GameClientMessage) callback.getData();
                log.info(getLogStartInfo() + "ask: " + message.getMessage());
                if (message.getMessage().startsWith("Mulligan")) {
                    session.sendPlayerBoolean(gameId, false);
                    return;
                } else {
                    log.error(getLogStartInfo() + "unknown GAME_ASK message: " + message.toString());
                }
                break;
            }

            case GAME_SELECT: {
                GameClientMessage message = (GameClientMessage) callback.getData();
                log.info(getLogStartInfo() + "select: " + message.getMessage());
                this.gameView = message.getGameView();

                // concede
                if (needToConcede) {
                    log.info(getLogStartInfo() + "game conceded");
                    needToConcede = false;
                    session.sendPlayerAction(PlayerAction.CONCEDE, gameId, null);
                    return;
                }

                // end priority step
                session.sendPlayerBoolean(gameId, false);
                return;
                /*
                if (LoadPhaseManager.getInstance().isSkip(message.getGameView(), message.getMessage(), playerId)) {
                    log.info(getLogStartInfo() + "Skipped: " + message.getMessage());
                    session.sendPlayerBoolean(gameId, false);
                } else {
                    log.error(getLogStartInfo() + "unknown GAME_SELECT or skips message: " + message.toString());
                }
                 */
                //break;
            }

            case GAME_OVER:
                log.info(getLogStartInfo() + "game over");
                gameOver = true;
                break;

            case END_GAME_INFO:
                GameEndView message = (GameEndView) callback.getData();
                this.gameResult = message.hasWon() ? "win" : "lose";
                log.info(getLogStartInfo() + "game end info, " + this.gameResult);
                break;

            // skip callbacks (no need to react)
            case JOINED_TABLE:
                break;

            default:
                log.error(getLogStartInfo() + "unknown callback: " + callback.getMethod() + ", " + callback.getData().toString());
                session.sendPlayerBoolean(gameId, false);
                break;
        }
    }

    private PlayerView getPlayer() {
        if ((this.gameView != null) && (this.playerId != null)) {
            for (PlayerView p : this.gameView.getPlayers()) {
                if (p.getPlayerId().equals(this.playerId)) {
                    return p;
                }
            }
        }
        return null;
    }

    private String getLogStartInfo() {
        String mes = "";
        PlayerView p = getPlayer();
        if (this.gameView != null && p != null && this.gameView.getStep() != null) {
            // never calls for client side client, cause it used as game's watcher, not a player
            mes += "T" + this.gameView.getTurn() + "-" + this.gameView.getStep().getIndex() + ", L:" + p.getLibraryCount() + ", H:" + getPlayer().getHandCount() + ": ";
        }

        return globalProgress + ", " + logsPrefix + ": " + mes;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    private void startControlThread() {
        new Thread(() -> {
            while (true) {
                controlCount++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (isGameOver()) {
                    return;
                }

                if (controlCount > 5) {
                    log.warn(getLogStartInfo() + "game seems frozen. Sending boolean message to server.");
                    session.sendPlayerBoolean(gameId, false);
                    controlCount = 0;
                }
            }

        }).start();
    }

    public void setConcede(boolean needToConcede) {
        this.needToConcede = needToConcede;
    }

    public String getLastGameResult() {
        return this.gameResult;
    }

    public GameView getLastGameView() {
        return this.gameView;
    }
}
