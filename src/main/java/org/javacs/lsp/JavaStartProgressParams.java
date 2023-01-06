package org.javacs.lsp;

public class JavaStartProgressParams extends JavaProgressParams {
  public JavaStartProgressParams(String token, String message) {
    super("begin", token, message);
  }
}
