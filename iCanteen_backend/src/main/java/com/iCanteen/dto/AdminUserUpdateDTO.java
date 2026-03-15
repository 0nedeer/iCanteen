package com.iCanteen.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminUserUpdateDTO {
    private String phone;
    private String password;
    private Integer role;
    private String nickname;
    private String icon;
    private String city;
    private String introduce;
    private Boolean gender;
    private LocalDate birthday;
    private Integer credits;
}

