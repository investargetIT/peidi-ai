package com.cyanrocks.ai.xiyan.models;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class SqlAttempt implements Serializable {
    private static final long serialVersionUID = 4302273289240548623L;

    private String sql;

    private String sqlError;
}
