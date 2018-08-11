package com.microsoft.identity.client;

public interface IIdToken {

    IAccountId getHomeAccountId();

    String getEnvironment();

    String getRealm();

    String getClientId();

    String getSecret();

}
