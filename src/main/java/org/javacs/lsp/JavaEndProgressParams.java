package org.javacs.lsp;

public class JavaEndProgressParams extends JavaProgressParams {
  public JavaEndProgressParams(String token, String message) {
    super("end", token, message);
  }
}
