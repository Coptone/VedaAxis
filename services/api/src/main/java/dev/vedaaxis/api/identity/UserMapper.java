package dev.vedaaxis.api.identity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface UserMapper {
    @Select("SELECT id, email, password_hash, created_at FROM app_user WHERE email = #{email}")
    Optional<UserRow> findByEmail(@Param("email") String email);

    @Select("SELECT id, email, password_hash, created_at FROM app_user WHERE id = #{id}")
    Optional<UserRow> findById(@Param("id") String id);

    @Insert("""
            INSERT INTO app_user(id, email, password_hash, created_at)
            VALUES(#{id}, #{email}, #{passwordHash}, #{createdAt})
            """)
    void insert(UserRow user);
}
