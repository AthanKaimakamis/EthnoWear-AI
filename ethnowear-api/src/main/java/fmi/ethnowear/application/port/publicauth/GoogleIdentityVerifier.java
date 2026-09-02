package fmi.ethnowear.application.port.publicauth;

import fmi.ethnowear.application.model.publicauth.GoogleIdentity;

public interface GoogleIdentityVerifier {
    GoogleIdentity verify(String credential, String expectedNonce);
}
