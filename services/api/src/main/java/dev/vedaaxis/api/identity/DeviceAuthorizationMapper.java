package dev.vedaaxis.api.identity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Mapper
public interface DeviceAuthorizationMapper {
    @Insert("""
            INSERT INTO device_authorization(
                id, device_code_hash, user_code, device_name, status, user_id, expires_at, consumed_at, created_at)
            VALUES(#{id}, #{deviceCodeHash}, #{userCode}, #{deviceName}, #{status}, #{userId}, #{expiresAt}, #{consumedAt}, #{createdAt})
            """)
    void insertAuthorization(DeviceAuthorizationRow authorization);

    @Select("""
            SELECT id, device_code_hash, user_code, device_name, status, user_id, expires_at, consumed_at, created_at
            FROM device_authorization WHERE device_code_hash = #{hash}
            """)
    Optional<DeviceAuthorizationRow> findByDeviceCodeHash(@Param("hash") String hash);

    @Select("""
            SELECT id, device_code_hash, user_code, device_name, status, user_id, expires_at, consumed_at, created_at
            FROM device_authorization WHERE user_code = #{userCode}
            """)
    Optional<DeviceAuthorizationRow> findByUserCode(@Param("userCode") String userCode);

    @Update("""
            UPDATE device_authorization
            SET status = 'APPROVED', user_id = #{userId}
            WHERE id = #{id} AND status = 'PENDING' AND expires_at > #{now}
            """)
    int approve(@Param("id") String id, @Param("userId") String userId, @Param("now") Instant now);

    @Update("""
            UPDATE device_authorization
            SET status = 'CONSUMED', consumed_at = #{consumedAt}
            WHERE id = #{id} AND status = 'APPROVED' AND consumed_at IS NULL
            """)
    int consume(@Param("id") String id, @Param("consumedAt") Instant consumedAt);

    @Insert("""
            INSERT INTO authorized_device(id, user_id, name, last_seen_at, revoked_at, created_at)
            VALUES(#{id}, #{userId}, #{name}, #{lastSeenAt}, #{revokedAt}, #{createdAt})
            """)
    void insertDevice(AuthorizedDeviceRow device);

    @Select("""
            SELECT id, user_id, name, last_seen_at, revoked_at, created_at
            FROM authorized_device WHERE user_id = #{userId} ORDER BY created_at DESC
            """)
    List<AuthorizedDeviceRow> listDevices(@Param("userId") String userId);

    @Update("""
            UPDATE authorized_device SET revoked_at = #{revokedAt}
            WHERE id = #{deviceId} AND user_id = #{userId} AND revoked_at IS NULL
            """)
    int revokeDevice(
            @Param("deviceId") String deviceId,
            @Param("userId") String userId,
            @Param("revokedAt") Instant revokedAt);
}
