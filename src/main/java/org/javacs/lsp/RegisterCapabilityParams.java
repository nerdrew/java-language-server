package org.javacs.lsp;

import java.util.*;
import com.google.gson.JsonArray;

public class RegisterCapabilityParams {
  public List<RegistrationParams> registrations;

  public RegisterCapabilityParams(List<RegistrationParams> registrations) {
    this.registrations = registrations;
  }
}
