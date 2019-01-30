package de.hpi.isg.dataprep.preparators;

import de.hpi.isg.dataprep.DialectBuilder;
import de.hpi.isg.dataprep.components.Pipeline;
import de.hpi.isg.dataprep.components.Preparation;
import de.hpi.isg.dataprep.context.DataContext;
import de.hpi.isg.dataprep.load.FlatFileDataLoader;
import de.hpi.isg.dataprep.load.SparkDataLoader;
import de.hpi.isg.dataprep.model.dialects.FileLoadDialect;
import de.hpi.isg.dataprep.model.target.system.AbstractPreparator;
import de.hpi.isg.dataprep.preparators.define.ChangeTableEncoding;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ChangeTableEncodingTest extends PreparatorTest {
    private static final String CSV_DIR = "./src/test/resources/encoding/";
    private static final String NO_ERRORS_URL = CSV_DIR + "no_encoding_error.csv";
    private static final String IN_CSV_URL = CSV_DIR + "error_character.csv";
    private static final String ERRORS_URL = CSV_DIR + "encoding_error.csv";
    private static final String ERRORS_AND_IN_CSV_URL = CSV_DIR + "both_error_and_error_character.csv";
    
    private static final String ENCODING = "UTF-8";
    private static DialectBuilder dialectBuilder;
    
    @BeforeClass
    public static void setUp() {
        Logger.getLogger("org").setLevel(Level.OFF);
        Logger.getLogger("akka").setLevel(Level.OFF);
        
        dialectBuilder = new DialectBuilder()
                .hasHeader(true)
                .inferSchema(true)
                .encoding(ENCODING);
    }
    
    // calApplicability
    
    @Test
    public void testNoErrors() {
        DataContext context = load(NO_ERRORS_URL);
        Assert.assertEquals(0, calApplicability(context), 0);
    }
    
    @Test
    public void testErrorCharsAlreadyInCSV() {
        DataContext context = load(IN_CSV_URL);
        Assert.assertEquals(0, calApplicability(context), 0);
    }
    
    @Test
    public void testWithErrors() {
        DataContext context = load(ERRORS_URL);
        Assert.assertTrue(calApplicability(context) > 0);
    }
    
    @Test
    public void testErrorsAndAlreadyInCSV() {
        DataContext context = load(ERRORS_AND_IN_CSV_URL);
        Assert.assertTrue(calApplicability(context) > 0);
    }
    
    private float calApplicability(DataContext context) {
        Pipeline pipeline = new Pipeline(context);
        pipeline.initMetadataRepository();
        
        AbstractPreparator preparator = new ChangeTableEncoding();
        pipeline.addPreparation(new Preparation(preparator));
        return preparator.calApplicability(null, pipeline.getRawData(), null);
    }
    
    private DataContext load(String url) {
        FileLoadDialect dialect = dialectBuilder.url(url).buildDialect();
        SparkDataLoader dataLoader = new FlatFileDataLoader(dialect);
        return dataLoader.load();
    }
    
    @Override
    public void cleanUpPipeline() {
    }
}
