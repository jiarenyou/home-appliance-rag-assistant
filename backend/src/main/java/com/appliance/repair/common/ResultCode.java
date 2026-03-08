package com.appliance.repair.common;

public enum ResultCode {
    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    DOCUMENT_PARSE_ERROR(1001, "文档解析失败"),
    DOCUMENT_VECTORIZATION_ERROR(1002, "文档向量化失败"),
    UNSUPPORTED_FILE_TYPE(1003, "不支持的文件类型"),
    LLM_ERROR(2001, "大模型调用失败"),
    NO_RELEVANT_CONTENT(2002, "未找到相关内容");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
