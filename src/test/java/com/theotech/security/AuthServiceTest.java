package com.theotech.security;

import com.theotech.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {

    @Test
    void acceptsLettersAndDigitsOfEightOrMore() {
        assertThatCode(() -> AuthService.validateStrength("Kigali2026")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> AuthService.validateStrength("Ab1"))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((ValidationException) e).getErrors())
                        .containsEntry("newPassword", "passwordLength"));
    }

    @Test
    void rejectsPasswordsWithoutDigitsOrLetters() {
        assertThatThrownBy(() -> AuthService.validateStrength("onlyletters")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> AuthService.validateStrength("1234567890")).isInstanceOf(ValidationException.class);
    }
}
