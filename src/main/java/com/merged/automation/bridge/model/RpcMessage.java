package com.merged.automation.bridge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Base RPC message structure
 */
public class RpcMessage {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("type")
    private MessageType type;
    
    @JsonProperty("method")
    private String method;
    
    @JsonProperty("params")
    private Map<String, Object> params;
    
    @JsonProperty("result")
    private Object result;
    
    @JsonProperty("error")
    private RpcError error;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    public RpcMessage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public RpcMessage(String id, MessageType type) {
        this();
        this.id = id;
        this.type = type;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    
    public RpcError getError() { return error; }
    public void setError(RpcError error) { this.error = error; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    // Enumeration for message types
    public enum MessageType {
        REQUEST, RESPONSE, NOTIFICATION, ERROR
    }
    
    // Error structure
    public static class RpcError {
        @JsonProperty("code")
        private int code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("data")
        private Object data;
        
        public RpcError() {}
        
        public RpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }
        
        public RpcError(int code, String message, Object data) {
            this(code, message);
            this.data = data;
        }
        
        // Getters and Setters
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
    }
    
    // Error codes
    public static class ErrorCodes {
        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;
        public static final int SERVER_ERROR = -32000;
        public static final int CLIENT_NOT_FOUND = -32001;
        public static final int AUTOMATION_ERROR = -32002;
        public static final int VNC_ERROR = -32003;
    }
}