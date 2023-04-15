package retrieval;

public class NumberReplacementTest {
    public static void main(String[] args) {
        String content = "this is a test 12.5 and another number 0.2 and another 5 alas";
        //content = content.replaceAll("\\d", "_NUM");
        //content = content.replaceAll("[0-9]*[0-9]","");
        content = content.replaceAll("(-)?\\d+(\\.\\d*)?", "_NUM_");
        System.out.println(content);

        String s = "employ_2.107918285930503";
        System.out.println(s.split("\\_").length);
    }

}
