package com.dnstore.backend.controller;

import com.dnstore.backend.model.Address;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.repository.AddressRepository;
import com.dnstore.backend.repository.UserRepository;
import com.dnstore.backend.service.JwtService;
import com.dnstore.backend.service.ResendEmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final int CODE_EXPIRY_MINUTES = 10;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ResendEmailService emailService;
    private final String challengeSecret;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            AddressRepository addressRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ResendEmailService emailService,
            @Value("${auth.challenge-secret:${jwt.secret}}") String challengeSecret) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.challengeSecret = challengeSecret;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email já cadastrado.");
        }
        if (request.getAddress() == null) {
            throw new IllegalArgumentException("Endereço é obrigatório.");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setCpf(request.getCpf());
        user.setRole(Role.USER);
        user.setEmailVerified(false);
        issueEmailVerification(user);
        userRepository.save(user);

        Address address = new Address();
        address.setUser(user);
        address.setStreet(request.getAddress().getStreet());
        address.setNumber(request.getAddress().getNumber());
        address.setComplement(request.getAddress().getComplement());
        address.setNeighborhood(request.getAddress().getNeighborhood());
        address.setCity(request.getAddress().getCity());
        address.setState(request.getAddress().getState());
        address.setZipCode(request.getAddress().getZipCode());
        addressRepository.save(address);

        return ResponseEntity.ok(new ChallengeResponse("EMAIL_VERIFICATION_REQUIRED", user.getEmail(), null));
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Optional<User> existingUser = userRepository.findByEmail(request.getEmail());
        if (existingUser.isPresent()
                && !existingUser.get().isEmailVerified()
                && passwordEncoder.matches(request.getPassword(), existingUser.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ChallengeResponse("EMAIL_VERIFICATION_REQUIRED", request.getEmail(), null));
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        User user = (User) authentication.getPrincipal();

        if (!user.isEmailMfaEnabled()) {
            return ResponseEntity.ok(authResponse(user));
        }
        if (cooldownActive(user.getMfaLastSentAt()))
            throw tooManyRequests();

        String challengeToken = randomToken();
        String code = randomCode();
        user.setMfaChallengeTokenHash(hash(challengeToken));
        user.setMfaCodeHash(hash(code));
        user.setMfaCodeExpiration(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));
        user.setMfaLastSentAt(LocalDateTime.now());
        user.setMfaAttempts(0);
        userRepository.save(user);
        emailService.sendMfaCode(user.getEmail(), code);
        return ResponseEntity.ok(new ChallengeResponse("MFA_REQUIRED", user.getEmail(), challengeToken));
    }

    @PostMapping("/verify-email")
    @Transactional
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody EmailCodeRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null)
            throw invalidCode();
        if (user.isEmailVerified())
            throw invalidCode();
        if (!isValidCode(user.getEmailVerificationCodeHash(), user.getEmailVerificationExpiration(),
                user.getEmailVerificationAttempts(), request.getCode())) {
            user.setEmailVerificationAttempts(user.getEmailVerificationAttempts() + 1);
            userRepository.save(user);
            throw invalidCode();
        }

        user.setEmailVerified(true);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiration(null);
        user.setEmailVerificationAttempts(0);
        userRepository.save(user);
        return ResponseEntity.ok(authResponse(user));
    }

    @PostMapping("/resend-verification")
    @Transactional
    public ResponseEntity<?> resendVerification(@Valid @RequestBody EmailRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null || user.isEmailVerified())
            return ResponseEntity.noContent().build();
        if (cooldownActive(user.getEmailVerificationLastSentAt()))
            throw tooManyRequests();
        issueEmailVerification(user);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-mfa")
    @Transactional
    public ResponseEntity<?> verifyMfa(@Valid @RequestBody MfaCodeRequest request) {
        User user = userRepository.findByMfaChallengeTokenHash(hash(request.getChallengeToken())).orElse(null);
        if (user == null || !isValidCode(user.getMfaCodeHash(), user.getMfaCodeExpiration(), user.getMfaAttempts(),
                request.getCode())) {
            if (user != null) {
                user.setMfaAttempts(user.getMfaAttempts() + 1);
                userRepository.save(user);
            }
            throw invalidCode();
        }

        clearMfaChallenge(user);
        userRepository.save(user);
        return ResponseEntity.ok(authResponse(user));
    }

    @PostMapping("/resend-mfa")
    @Transactional
    public ResponseEntity<?> resendMfa(@Valid @RequestBody MfaChallengeRequest request) {
        User user = userRepository.findByMfaChallengeTokenHash(hash(request.getChallengeToken())).orElse(null);
        if (user == null || user.getMfaCodeExpiration() == null
                || user.getMfaCodeExpiration().isBefore(LocalDateTime.now())) {
            throw invalidCode();
        }
        if (cooldownActive(user.getMfaLastSentAt()))
            throw tooManyRequests();

        String code = randomCode();
        user.setMfaCodeHash(hash(code));
        user.setMfaCodeExpiration(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));
        user.setMfaLastSentAt(LocalDateTime.now());
        user.setMfaAttempts(0);
        userRepository.save(user);
        emailService.sendMfaCode(user.getEmail(), code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Transactional
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody EmailRequest request) {
        Optional<User> optionalUser = userRepository.findByEmail(request.getEmail());
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (!cooldownActive(user.getResetTokenLastSentAt())) {
                String resetToken = randomToken();
                user.setResetToken(hash(resetToken));
                user.setResetTokenExpiration(LocalDateTime.now().plusMinutes(30));
                user.setResetTokenLastSentAt(LocalDateTime.now());
                userRepository.save(user);
                try {
                    emailService.sendPasswordResetLink(user.getEmail(), resetToken);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Password reset email could not be delivered");
                }
            }
        }
        return ResponseEntity.ok(new MessageResponse("If the account exists, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(hash(request.getToken())).orElse(null);
        if (user == null || user.getResetTokenExpiration() == null
                || user.getResetTokenExpiration().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Link de redefinição inválido ou expirado.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiration(null);
        clearMfaChallenge(user);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("A senha atual está incorreta.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiration(null);
        clearMfaChallenge(user);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    private void issueEmailVerification(User user) {
        String code = randomCode();
        user.setEmailVerificationCodeHash(hash(code));
        user.setEmailVerificationExpiration(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));
        user.setEmailVerificationLastSentAt(LocalDateTime.now());
        user.setEmailVerificationAttempts(0);
        emailService.sendVerificationCode(user.getEmail(), code);
    }

    private boolean isValidCode(String storedHash, LocalDateTime expiration, int attempts, String code) {
        return storedHash != null
                && expiration != null
                && expiration.isAfter(LocalDateTime.now())
                && attempts < MAX_CODE_ATTEMPTS
                && MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.UTF_8),
                        hash(code).getBytes(StandardCharsets.UTF_8));
    }

    private boolean cooldownActive(LocalDateTime lastSentAt) {
        return lastSentAt != null && lastSentAt.plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now());
    }

    private void clearMfaChallenge(User user) {
        user.setMfaChallengeTokenHash(null);
        user.setMfaCodeHash(null);
        user.setMfaCodeExpiration(null);
        user.setMfaAttempts(0);
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse("AUTHENTICATED", jwtService.generateToken(user), new UserResponse(user));
    }

    private static String randomCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hash(String value) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(challengeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = hmac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static IllegalArgumentException invalidCode() {
        return new IllegalArgumentException("Código inválido, expirado ou com limite de tentativas excedido.");
    }

    private static org.springframework.web.server.ResponseStatusException tooManyRequests() {
        return new org.springframework.web.server.ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "A new code can be requested in 60 seconds.");
    }

    public record ChallengeResponse(String status, String email, String challengeToken) {
    }

    public record AuthResponse(String status, String token, UserResponse user) {
    }

    public record MessageResponse(String message) {
    }

    public record UserResponse(java.util.UUID id, String name, String email, String phone, Role role) {
        public UserResponse(User user) {
            this(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole());
        }
    }

    public static class EmailRequest {
        @NotBlank
        @Email
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class EmailCodeRequest extends EmailRequest {
        @NotBlank
        @Pattern(regexp = "\\d{6}")
        private String code;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static class MfaChallengeRequest {
        @NotBlank
        private String challengeToken;

        public String getChallengeToken() {
            return challengeToken;
        }

        public void setChallengeToken(String challengeToken) {
            this.challengeToken = challengeToken;
        }
    }

    public static class MfaCodeRequest extends MfaChallengeRequest {
        @NotBlank
        @Pattern(regexp = "\\d{6}")
        private String code;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static class ResetPasswordRequest {
        @NotBlank
        private String token;
        @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(regexp = "(?=.*[A-Za-z])(?=.*\\d).+")
        private String newPassword;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(regexp = "(?=.*[A-Za-z])(?=.*\\d).+")
        private String newPassword;

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    public static class RegisterRequest {
        @NotBlank(message = "Name is required")
        @Size(max = 120)
        private String name;
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 150)
        private String email;
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128)
        @Pattern(regexp = "(?=.*[A-Za-z])(?=.*\\d).+")
        private String password;
        private String phone;
        @NotBlank
        @Pattern(regexp = "\\d{11}")
        private String cpf;
        @Valid
        private AddressRequest address;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getCpf() {
            return cpf;
        }

        public void setCpf(String cpf) {
            this.cpf = cpf;
        }

        public AddressRequest getAddress() {
            return address;
        }

        public void setAddress(AddressRequest address) {
            this.address = address;
        }
    }

    public static class AddressRequest {
        @NotBlank
        private String street;
        @NotBlank
        private String number;
        private String complement;
        @NotBlank
        private String neighborhood;
        @NotBlank
        private String city;
        @NotBlank
        private String state;
        @NotBlank
        private String zipCode;

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number;
        }

        public String getComplement() {
            return complement;
        }

        public void setComplement(String complement) {
            this.complement = complement;
        }

        public String getNeighborhood() {
            return neighborhood;
        }

        public void setNeighborhood(String neighborhood) {
            this.neighborhood = neighborhood;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getZipCode() {
            return zipCode;
        }

        public void setZipCode(String zipCode) {
            this.zipCode = zipCode;
        }
    }

    public static class LoginRequest {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
