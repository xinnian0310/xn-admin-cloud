package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.CaptchaVO;
import com.smartadmin.dto.LoginPageConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String PENDING = "PENDING";
    private static final String VERIFIED = "VERIFIED";

    private final AppKvStore kvStore;
    private final LoginPageConfigService loginPageConfigService;
    private final SecurityPolicyService securityPolicyService;

    /** 按当前启用的登录页配置生成验证码；未开启则返回 null。 */
    public CaptchaVO create() {
        LoginPageConfigVO active = loginPageConfigService.getActive();
        if (active == null || !Boolean.TRUE.equals(active.getCaptchaEnabled())) {
            return null;
        }
        String type = StringUtils.hasText(active.getCaptchaType()) ? active.getCaptchaType().toUpperCase(Locale.ROOT) : "IMAGE";
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        Duration ttl = Duration.ofSeconds(Math.max(30, securityPolicyService.effectiveLogin().getCaptchaTtlSeconds()));

        if ("SLIDER".equals(type)) {
            kvStore.set(KEY_PREFIX + captchaId, PENDING, ttl);
            return new CaptchaVO(captchaId, "SLIDER", null);
        }

        String code = randomCode(4);
        kvStore.set(KEY_PREFIX + captchaId, code, ttl);
        return new CaptchaVO(captchaId, "IMAGE", renderImageBase64(code));
    }

    /** 滑块拖动完成后调用，将 PENDING 标记为 VERIFIED（一次性）。 */
    public void verifySlider(String captchaId, int percent) {
        if (!StringUtils.hasText(captchaId)) {
            throw new BusinessException("验证码无效或已过期");
        }
        String key = KEY_PREFIX + captchaId.trim();
        String stored = kvStore.get(key);
        if (!StringUtils.hasText(stored)) {
            throw new BusinessException("验证码无效或已过期");
        }
        if (VERIFIED.equals(stored)) {
            return;
        }
        if (!PENDING.equals(stored)) {
            throw new BusinessException("验证码类型不正确");
        }
        if (percent < 92) {
            throw new BusinessException("请完成滑块验证");
        }
        Duration ttl = Duration.ofSeconds(Math.max(30, securityPolicyService.effectiveLogin().getCaptchaTtlSeconds()));
        kvStore.set(key, VERIFIED, ttl);
    }

    /**
     * 登录前校验。若登录页未开启验证码则跳过。
     * IMAGE：比对用户输入；SLIDER：须已 verifySlider。
     * 校验成功后立即失效，不可复用。
     */
    public void validateForLogin(String captchaId, String captchaCode) {
        LoginPageConfigVO active = loginPageConfigService.getActive();
        if (active == null || !Boolean.TRUE.equals(active.getCaptchaEnabled())) {
            return;
        }
        if (!StringUtils.hasText(captchaId)) {
            throw new BusinessException("请完成验证码校验");
        }
        String key = KEY_PREFIX + captchaId.trim();
        String stored = kvStore.get(key);
        if (!StringUtils.hasText(stored)) {
            throw new BusinessException("验证码无效或已过期");
        }

        String type = StringUtils.hasText(active.getCaptchaType()) ? active.getCaptchaType().toUpperCase(Locale.ROOT) : "IMAGE";
        boolean ok;
        if ("SLIDER".equals(type)) {
            ok = VERIFIED.equals(stored);
        } else {
            ok = StringUtils.hasText(captchaCode)
                    && stored.equalsIgnoreCase(captchaCode.trim());
        }
        kvStore.delete(key);
        if (!ok) {
            throw new BusinessException("验证码不正确");
        }
    }

    private static String randomCode(int len) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private static String renderImageBase64(String code) {
        int w = 110;
        int h = 40;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(240, 244, 248));
        g.fillRect(0, 0, w, h);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(r.nextInt(160), r.nextInt(160), r.nextInt(160), 150));
            g.drawLine(r.nextInt(w), r.nextInt(h), r.nextInt(w), r.nextInt(h));
        }
        for (int i = 0; i < code.length(); i++) {
            g.setFont(new Font("SansSerif", Font.BOLD, 22 + r.nextInt(4)));
            g.setColor(new Color(40 + r.nextInt(100), 40 + r.nextInt(100), 40 + r.nextInt(100)));
            double angle = (r.nextDouble() - 0.5) * 0.4;
            int x = 14 + i * 22;
            int y = 28;
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.rotate(-angle, x, y);
        }
        g.dispose();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception ex) {
            throw new BusinessException("生成验证码失败");
        }
    }
}
