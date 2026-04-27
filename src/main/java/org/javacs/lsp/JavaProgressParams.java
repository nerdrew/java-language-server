package org.javacs.lsp;

public class JavaProgressParams {
  private String token;
  private JavaProgressValue value;

  public enum Kind {
    BEGIN("begin"),
    REPORT("report"),
    END("end");

    public final String name;

    Kind(String name) {
      this.name = name;
    }
  }

  public class JavaProgressValue {
    private String message;
    private String kind;
    private boolean cancellable = false;

    public JavaProgressValue(String kind, String message) {
      this.kind = kind;
      this.message = message;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public String getKind() {
      return kind;
    }

    public boolean getCancellable() {
      return cancellable;
    }
  }

  private JavaProgressParams(Kind kind, String token, String message) {
    this.token = token;
    this.value = new JavaProgressValue(kind.name, message);
  }

  public static JavaProgressParams begin(String token, String message) {
    return new JavaProgressParams(Kind.BEGIN, token, message);
  }
  public static JavaProgressParams report(String token, String message) {
    return new JavaProgressParams(Kind.REPORT, token, message);
  }
  public static JavaProgressParams end(String token, String message) {
    return new JavaProgressParams(Kind.END, token, message);
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
