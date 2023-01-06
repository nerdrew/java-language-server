package org.javacs.lsp;

public class JavaReportProgressParams extends JavaProgressParams {
  public JavaReportProgressParams(String token, String message) {
    super("report", token, message);
  }
}
