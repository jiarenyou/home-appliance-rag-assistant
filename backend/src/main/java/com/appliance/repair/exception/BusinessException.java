package com.appliance.repair.exception;

import com.appliance.repair.common.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    private final String message;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(ResultCode resultCode, String detailMessage) {
        super(resultCode.getMessage() + ": " + detailMessage);
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage() + ": " + detailMessage;
    }
}
