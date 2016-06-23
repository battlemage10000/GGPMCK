import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.util.List;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class MckTranslatorTest {
	
	String emptyGdlUri = "file:/Users/vedantds/Dropbox/Masters/MCK/MckTranslator/test/gdlii/empty.gdl";
	String testGdlUri = "file:/Users/vedantds/Dropbox/Masters/MCK/MckTranslator/test/gdlii/testGame.gdl";
	
	@Test
	public void loadEmptyGameDescription() {
		try{
			List<String> tokens = MckTranslator.tokenizer(emptyGdlUri);
			assertEquals(tokens.isEmpty(), true);
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void loadUnformatedValidGameDescription() {
		try{
			List<String> tokens = MckTranslator.tokenizer(testGdlUri);
			assertEquals(tokens.isEmpty(), false);
			
			List<String> expectedList = Arrays.asList("(",")","(","init",")");
			assertEquals(tokens, expectedList);
			
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
}