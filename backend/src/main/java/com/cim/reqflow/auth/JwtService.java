package com.cim.reqflow.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。
 * accessToken 放 Authorization 请求头，refreshToken 放 HttpOnly Cookie；
 * 二者使用不同密钥，避免刷新令牌被当成访问令牌直接使用。
 * 密钥从配置读取，生产环境由环境变量注入，绝不入库入 git。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Service
public class JwtService {

    /** 每秒毫秒数，用于把小时配置换算成毫秒 */
    private static final long MILLIS_PER_HOUR = 3_600_000L;

    /** 用户名声明键 */
    private static final String CLAIM_USERNAME = "username";

    /** 令牌类型声明键，用于区分访问令牌与刷新令牌 */
    private static final String CLAIM_TOKEN_KIND = "tokenKind";

    /** 刷新令牌类型值 */
    private static final String TOKEN_KIND_REFRESH = "refresh";

    /** 访问令牌签名密钥 */
    private final SecretKey accessKey;

    /** 刷新令牌签名密钥 */
    private final SecretKey refreshKey;

    /** 访问令牌有效期（毫秒） */
    private final long accessTtlMillis;

    /** 刷新令牌有效期（毫秒） */
    private final long refreshTtlMillis;

    public JwtService(@Value("${cim.jwt.access-secret}") String accessSecret,
                      @Value("${cim.jwt.refresh-secret}") String refreshSecret,
                      @Value("${cim.jwt.access-hours}") long accessHours,
                      @Value("${cim.jwt.refresh-hours}") long refreshHours) {
        this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMillis = accessHours * MILLIS_PER_HOUR;
        this.refreshTtlMillis = refreshHours * MILLIS_PER_HOUR;
    }

    /**
     * 签发访问令牌。subject 取用户 ID，附带用户名便于日志排查。
     *
     * @param userId   用户 ID
     * @param username 登录账号
     * @return 访问令牌
     */
    public String createAccessToken(String userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_USERNAME, username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTtlMillis))
                .signWith(accessKey)
                .compact();
    }

    /**
     * 签发刷新令牌，额外带 tokenKind 声明，防止与访问令牌混用。
     *
     * @param userId   用户 ID
     * @param username 登录账号
     * @return 刷新令牌
     */
    public String createRefreshToken(String userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TOKEN_KIND, TOKEN_KIND_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTtlMillis))
                .signWith(refreshKey)
                .compact();
    }

    /**
     * 解析并验签访问令牌。
     *
     * @param token 访问令牌
     * @return 令牌声明
     */
    public Claims parseAccessToken(String token) {
        return Jwts.parser().verifyWith(accessKey).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 解析并验签刷新令牌。
     *
     * @param token 刷新令牌
     * @return 令牌声明
     */
    public Claims parseRefreshToken(String token) {
        return Jwts.parser().verifyWith(refreshKey).build().parseSignedClaims(token).getPayload();
    }
}
