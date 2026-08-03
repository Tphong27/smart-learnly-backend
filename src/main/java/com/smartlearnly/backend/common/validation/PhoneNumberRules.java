package com.smartlearnly.backend.common.validation;

public final class PhoneNumberRules {
    public static final String VIETNAMESE_MOBILE_PATTERN =
            "^(?:$|(?:0|\\+84)[35789][0-9]{8})$";
    public static final String VIETNAMESE_MOBILE_MESSAGE =
            "Phone number must be a valid Vietnamese mobile number, for example 0901234567 or +84901234567";

    private PhoneNumberRules() {
    }
}
