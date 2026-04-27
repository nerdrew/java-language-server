package org.javacs.lsp;

import com.google.gson.JsonElement;

public class Message {
    public String jsonrpc;
    public Integer id;
    public String method;
    public JsonElement params;

    public String toString() {
        return String.format("jsonrpc=%s id=%d method=%s params=%s", jsonrpc, id, method, params);
    }
}
