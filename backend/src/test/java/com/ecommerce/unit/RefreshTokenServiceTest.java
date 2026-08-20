package com.ecommerce.unit;

import com.ecommerce.entity.RefreshToken;
import com.ecommerce.entity.User;
import com.ecommerce.exception.InvalidTokenException;
import com.ecommerce.repository.RefreshTokenRepository;
import com.ecommerce.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User testUser;
    private final String rawToken = "my-secret-refresh-token";
    private final long expirationMs = 1000 * 60 * 60 * 24 * 7L; // 7 days

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
    }

    @Test
    void createRefreshToken_ShouldHashTokenAndSaveToDatabase() {
        // Arrange
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        RefreshToken savedToken = refreshTokenService.createRefreshToken(testUser, rawToken, expirationMs);

        // Assert
        assertNotNull(savedToken);
        assertEquals(testUser, savedToken.getUser());
        assertFalse(savedToken.isRevoked());
        assertNotNull(savedToken.getExpiryDate());

        // Verify save was called and token is hashed
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(1)).save(captor.capture());
        
        String savedHash = captor.getValue().getTokenHash();
        assertNotNull(savedHash);
        assertNotEquals(rawToken, savedHash); // Ensure it's not saved as plaintext
    }

    @Test
    void verifyNotRevoked_WhenTokenIsValidAndNotRevoked_ShouldNotThrowException() {
        // Arrange
        RefreshToken token = new RefreshToken();
        token.setRevoked(false);
        
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        // Act & Assert
        assertDoesNotThrow(() -> refreshTokenService.verifyNotRevoked(rawToken));
    }

    @Test
    void verifyNotRevoked_WhenTokenIsRevoked_ShouldThrowInvalidTokenException() {
        // Arrange
        RefreshToken token = new RefreshToken();
        token.setRevoked(true);
        token.setUser(testUser);
        
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        // Act & Assert
        InvalidTokenException exception = assertThrows(InvalidTokenException.class, 
            () -> refreshTokenService.verifyNotRevoked(rawToken));
            
        assertEquals("Detected reuse of a revoked refresh token. Please login again", exception.getMessage());
        verify(refreshTokenRepository, times(1)).revokeAllUserTokens(testUser.getId());
    }

    @Test
    void verifyNotRevoked_WhenTokenNotFound_ShouldThrowInvalidTokenException() {
        // Arrange
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        InvalidTokenException exception = assertThrows(InvalidTokenException.class, 
            () -> refreshTokenService.verifyNotRevoked(rawToken));
            
        assertEquals("Refresh token does not exist in database", exception.getMessage());
    }

    @Test
    void revokeToken_WhenTokenExists_ShouldSetRevokedToTrueAndSave() {
        // Arrange
        RefreshToken token = new RefreshToken();
        token.setRevoked(false);
        token.setUser(testUser);
        
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        // Act
        refreshTokenService.revokeToken(rawToken);

        // Assert
        assertTrue(token.isRevoked());
        verify(refreshTokenRepository, times(1)).save(token);
    }

    @Test
    void revokeToken_WhenTokenNotFound_ShouldDoNothing() {
        // Arrange
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertDoesNotThrow(() -> refreshTokenService.revokeToken(rawToken));
        verify(refreshTokenRepository, never()).save(any());
    }
}
