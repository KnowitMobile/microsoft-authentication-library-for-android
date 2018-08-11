package com.microsoft.identity.client;

public class AuthenticationResultV2 {

    private IAccount mAccount;

    private IAccessToken mAccessToken;

    private IIdToken mIdtoken;

    public IAccount getAccount() {
        return mAccount;
    }

    public IAccessToken getAccessToken() {
        return mAccessToken;
    }

    public IIdToken getIdToken() {
        return mIdtoken;
    }
}
