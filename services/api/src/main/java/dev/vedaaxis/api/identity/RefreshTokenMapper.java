package dev.vedaaxis.api.identity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.Optional;

@Mapper
public interface RefreshTokenMapper {
    @Insert("""
            INSERT INTO refresh_token(id, user_id, token_hash, audience, expires_at, revoked_at, created_at)
            VALUES(#{id}, #{userId}, #{tokenHash}, #{audience}, #{expiresAt}, #{revokedAt}, #{createdAt})
            """)
    void insert(RefreshTokenRow token);

    @Select("""
            SELECT id, user_id, token_hash, audience, expires_at, revoked_at, created_at
            FROM refresh_token WHERE token_hash = #{tokenHash}
            """)
    Optional<RefreshTokenRow> findByHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE refresh_token SET revoked_at = #{revokedAt} WHERE id = #{id} AND revoked_at IS NULL")
    int revoke(@Param("id") String id, @Param("revokedAt") Instant revokedAt);

    @Update("UPDATE refresh_token SET revoked_at = #{revokedAt} WHERE user_id = #{userId} AND audience = #{audience} AND revoked_at IS NULL")
    int revokeAudience(@Param("userId") String userId, @Param("audience") String audience, @Param("revokedAt") Instant revokedAt);
}
