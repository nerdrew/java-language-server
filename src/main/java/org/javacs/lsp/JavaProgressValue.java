package org.javacs.lsp;

public class JavaProgressValue {
  private String message;
  private String title;
  private String kind;
  private boolean cancellable = false;

  public JavaProgressValue(String kind, String title, String message) {
    this.kind = kind;
    this.title = title;
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getTitle() {
    return title;
  }

  public String getKind() {
    return kind;
  }

  public boolean getCancellable() {
    return cancellable;
  }
}
