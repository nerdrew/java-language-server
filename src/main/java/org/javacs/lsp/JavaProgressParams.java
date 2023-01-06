package org.javacs.lsp;

public abstract class JavaProgressParams {
  private String token;
  private JavaProgressValue value;

  public JavaProgressParams(String kind, String token, String message) {
    this.token = token;
    this.value = new JavaProgressValue(kind, null, message);
  }

  public String getToken() {
    return token;
  }

  public JavaProgressValue getValue() {
    return value;
  }

  public void setMessage(String message) {
    this.value.setMessage(message);
  }
}
