package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ArmorMitigationRulesTest {

    @Test
    void environmentalDamageWinsWhenDamageAlsoBypassesArmor() {
        assertEquals(
                ArmorMitigationRules.DamageClass.ENVIRONMENT,
                ArmorMitigationRules.classify(true, true));
    }

    @Test
    void matrixShieldCancelsEnvironmentalDamage() {
        assertEquals(
                0.0F,
                ArmorMitigationRules.apply(
                        "matrix_shield",
                        ArmorMitigationRules.DamageClass.ENVIRONMENT,
                        8.0F));
    }

    @Test
    void hardDamageRemainsHalfEffectiveAgainstMatrixShield() {
        assertEquals(
                4.0F,
                ArmorMitigationRules.apply(
                        "matrix_shield",
                        ArmorMitigationRules.DamageClass.HARD,
                        8.0F));
    }
}
