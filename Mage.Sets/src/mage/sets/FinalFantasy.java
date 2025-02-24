package mage.sets;

import mage.cards.ExpansionSet;
import mage.constants.Rarity;
import mage.constants.SetType;

/**
 * @author TheElk801
 */
public final class FinalFantasy extends ExpansionSet {

    private static final FinalFantasy instance = new FinalFantasy();

    public static FinalFantasy getInstance() {
        return instance;
    }

    private FinalFantasy() {
        super("Final Fantasy", "FIN", ExpansionSet.buildDate(2025, 6, 13), SetType.EXPANSION);
        this.blockName = "Final Fantasy"; // for sorting in GUI
        this.hasBasicLands = false; // temporary

        cards.add(new SetCardInfo("Sazh's Chocobo", 200, Rarity.UNCOMMON, mage.cards.s.SazhsChocobo.class));
    }
}
