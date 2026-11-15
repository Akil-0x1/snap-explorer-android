package local.snapexplorer.mobile;
public final class ChatPresentationTest {
    public static void main(String[] args){
        check(!ChatPresentation.visible("[VIDEO]","VIDEO",0));
        check(!ChatPresentation.visible("[IMAGE]","IMAGE",0));
        check(!ChatPresentation.visible("[STICKER]","STICKER",0));
        check(ChatPresentation.visible("[VIDEO]","VIDEO",1));
        check(ChatPresentation.visible("A real caption","VIDEO",0));
        check(ChatPresentation.visible("Normal message","TEXT",0));
        check(ChatPresentation.visible("[a user-written aside]","TEXT",0));
        check(!ChatPresentation.visible("","IMAGE",0));
        System.out.println("8 unavailable-media presentation checks passed.");
    }
    private static void check(boolean value){if(!value)throw new AssertionError("Media presentation mismatch");}
}
