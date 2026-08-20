package fmi.ethnowear.tools.password;

public final class PasswordTool {

    private PasswordTool() {
    }

    public static void main(String[] args) {
        GeneratedPassword generated = new PasswordGenerator().generate();

        System.out.println("Temporary password: " + generated.password());
        System.out.println("Password hash: " + generated.passwordHash());
    }
}
