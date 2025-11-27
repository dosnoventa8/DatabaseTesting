package com.praktikum.database.testing.library.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Integer userId;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private String status;
    private Timestamp registrationDate;
    private Timestamp lastLogin;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}