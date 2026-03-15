package com.iCanteen;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.iCanteen.mapper")
@SpringBootApplication
public class iCanteenApplication {

    public static void main(String[] args) {
        SpringApplication.run(iCanteenApplication.class, args);
    }

}
