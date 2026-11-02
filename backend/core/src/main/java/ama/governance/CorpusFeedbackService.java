package ama.governance;
import java.util.regex.Pattern;
/** Common direct identifiers are concealed in reviewer/global views; the submitter retains the original. */
public final class CorpusFeedbackService {
 private CorpusFeedbackService(){}
 private static final Pattern EMAIL=Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
 private static final Pattern ID=Pattern.compile("(?<![0-9])[0-9]{17}[0-9Xx](?![0-9])");
 private static final Pattern PHONE=Pattern.compile("(?<![0-9])1[3-9][0-9]{9}(?![0-9])");
 public static String redact(String text){if(text==null)return null;String value=EMAIL.matcher(text).replaceAll("[邮箱已隐藏]");value=ID.matcher(value).replaceAll("[证件号已隐藏]");return PHONE.matcher(value).replaceAll("[手机号已隐藏]");}
}
