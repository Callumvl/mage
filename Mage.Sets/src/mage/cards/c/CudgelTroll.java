

package mage.cards.c;

import java.util.UUID;
import mage.MageInt;
import mage.abilities.common.SimpleActivatedAbility;
import mage.abilities.costs.mana.ManaCostsImpl;
import mage.abilities.effects.common.RegenerateSourceEffect;
import mage.cards.CardImpl;
import mage.cards.CardSetInfo;
import mage.constants.CardType;
import mage.constants.SubType;
import mage.constants.Zone;

/**
 *
 * @author BetaSteward_at_googlemail.com
 */
public final class CudgelTroll extends CardImpl {

    public CudgelTroll(UUID ownerId, CardSetInfo setInfo) {
        super(ownerId,setInfo,new CardType[]{CardType.CREATURE},"{2}{G}{G}");

        this.subtype.add(SubType.TROLL);
        this.power = new MageInt(4);
        this.toughness = new MageInt(3);

        this.addAbility(new SimpleActivatedAbility(new RegenerateSourceEffect(), new ManaCostsImpl<>("{G}")));
    }

    private CudgelTroll(final CudgelTroll card) {
        super(card);
    }

    @Override
    public CudgelTroll copy() {
        return new CudgelTroll(this);
    }

}
