package com.iCanteen.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserInfoUpdateDTO {
    private String phone;
    private String password;
    private String nickname;
    private String icon;
    private String city;
    private String introduce;
    private Boolean gender;
    private LocalDate birthday;
}
