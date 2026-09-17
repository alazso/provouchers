package so.alaz.provouchers.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SoundsTest {

    @Test
    void blankSpecIsAValidNoOp() {
        assertThat(Sounds.isValid("")).isTrue();
        assertThat(Sounds.isValid("   ")).isTrue();
    }

    @Test
    void keyIsCheckedInLowerCase() {
        assertThat(Sounds.isValid("minecraft:entity.player.levelup")).isTrue();
        assertThat(Sounds.isValid("MINECRAFT:ENTITY.PLAYER.LEVELUP 1 1")).isTrue();
        assertThat(Sounds.isValid("entity.player.levelup")).isTrue();   // the namespace defaults to minecraft
    }

    @Test
    void unparseableKeyIsInvalid() {
        assertThat(Sounds.isValid("bad$key")).isFalse();
        assertThat(Sounds.isValid("minecraft:a:b 1 1")).isFalse();
    }

    @Test
    void volumeAndPitchAreNotChecked() {
        assertThat(Sounds.isValid("minecraft:block.note_block.pling loud high")).isTrue();
    }
}
