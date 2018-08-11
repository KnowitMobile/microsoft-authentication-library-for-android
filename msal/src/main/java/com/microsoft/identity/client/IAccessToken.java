package com.microsoft.identity.client;

import java.util.Date;

public interface IAccessToken {

    IAccountId getHomeAccountId();

    String getRealm();

    String getEnvironment();

    String getClientId();

    String getTarget();

    Date getCachedAt();

    Date getExpiresOn();

    String getSecret();
}
