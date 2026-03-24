package com.vaulthub.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final JavaMailSender mailSender;

    // email → [otp, expiryEpochSeconds, attemptCount]
    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    private static final int OTP_EXPIRY_SECONDS = 600; // 10 min
    private static final int MAX_ATTEMPTS = 5;

    // ── Generate and send OTP ──────────────────────────────────────────
    public void sendOtp(String email, String fullName) {
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        long expiry = Instant.now().getEpochSecond() + OTP_EXPIRY_SECONDS;
        store.put(email.toLowerCase(), new OtpEntry(otp, expiry, 0));

        log.info("📧 Sending OTP to {}", email);
        sendEmail(email, fullName, otp);
    }

    // ── Verify OTP ─────────────────────────────────────────────────────
    public OtpResult verify(String email, String otp) {
        String key = email.toLowerCase();
        OtpEntry entry = store.get(key);

        if (entry == null) return OtpResult.NOT_FOUND;
        if (Instant.now().getEpochSecond() > entry.expiry) {
            store.remove(key);
            return OtpResult.EXPIRED;
        }
        if (entry.attempts >= MAX_ATTEMPTS) return OtpResult.TOO_MANY;

        if (entry.otp.equals(otp.trim())) {
            store.remove(key);  // single-use
            return OtpResult.OK;
        } else {
            entry.attempts++;
            return OtpResult.WRONG;
        }
    }

    public void clear(String email) {
        store.remove(email.toLowerCase());
    }

    // ── Email HTML ─────────────────────────────────────────────────────
    private void sendEmail(String to, String name, String otp) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");
            h.setTo(to);
            h.setFrom("harshad.deshmukh82004@gmail.com", "VaultHub Security");
            h.setSubject("🔐 Your VaultHub Password Reset OTP");
            h.setText(buildHtml(name, otp), true);
            mailSender.send(msg);
            log.info("✅ OTP sent to {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to send OTP email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send OTP email. Please try again.");
        }
    }

    private String buildHtml(String name, String otp) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"/></head>
            <body style="margin:0;padding:0;background:#080810;font-family:'Segoe UI',sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#080810;padding:40px 20px;">
                <tr><td align="center">
                  <table width="480" cellpadding="0" cellspacing="0"
                         style="background:#0e0e1a;border:1px solid #1e1e32;border-radius:16px;overflow:hidden;">
                    <tr>
                      <td style="background:linear-gradient(135deg,#6366f1,#818cf8);padding:28px 32px;">
                        <p style="margin:0;font-size:22px;font-weight:800;color:#fff;">🗂️ VaultHub</p>
                        <p style="margin:6px 0 0;color:rgba(255,255,255,0.75);font-size:13px;">Password Reset Request</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:32px;">
                        <p style="color:#e8e8f5;font-size:15px;margin:0 0 8px;">Hi <strong>%s</strong>,</p>
                        <p style="color:#8888aa;font-size:13px;margin:0 0 28px;line-height:1.6;">
                          We received a request to reset your VaultHub password. Use the OTP below.
                          It expires in <strong style="color:#e8e8f5;">10 minutes</strong>.
                        </p>
                        <div style="text-align:center;background:#13131f;border:1px dashed #2a2a45;border-radius:12px;padding:24px 16px;margin-bottom:28px;">
                          <p style="margin:0 0 8px;color:#52526e;font-size:11px;letter-spacing:2px;text-transform:uppercase;">One-Time Password</p>
                          <p style="margin:0;font-size:42px;font-weight:800;letter-spacing:12px;color:#818cf8;font-family:'Courier New',monospace;">%s</p>
                        </div>
                        <p style="color:#52526e;font-size:12px;margin:0;line-height:1.6;">
                          If you didn't request this, you can safely ignore this email.<br/>
                          Your password will not change until you use this OTP.
                        </p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:16px 32px;border-top:1px solid #1e1e32;text-align:center;">
                        <p style="margin:0;color:#3a3a55;font-size:11px;">© 2024 VaultHub · Secure File Storage</p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(name, otp);
    }

    // ── Inner types ────────────────────────────────────────────────────
    private static class OtpEntry {
        String otp;
        long expiry;
        int attempts;
        OtpEntry(String otp, long expiry, int attempts) {
            this.otp = otp; this.expiry = expiry; this.attempts = attempts;
        }
    }

    public enum OtpResult { OK, WRONG, EXPIRED, NOT_FOUND, TOO_MANY }
}
