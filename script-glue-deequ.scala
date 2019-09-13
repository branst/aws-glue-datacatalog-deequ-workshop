import com.amazonaws.services.glue.util.JsonOptions
import com.amazonaws.services.glue.{DynamicFrame, GlueContext}
import org.apache.spark.SparkContext


import com.amazon.deequ.{VerificationSuite, VerificationResult}
import com.amazon.deequ.VerificationResult.checkResultsAsDataFrame
import com.amazon.deequ.checks.{Check, CheckLevel}


object GlueApp{
    
   def main(sysArgs: Array[String]) = {
       
        val sc: SparkContext = new SparkContext()
        val glueContext: GlueContext = new GlueContext(sc)
        val sparkSession = glueContext.getSparkSession
        
        val ouputDeequDir = "s3://bucket-glue-target/target/deequ/productos_results"
        
        val dataset = sparkSession.read.parquet("s3://bucket-glue-target/target/productos")

        val verificationResult: VerificationResult = { VerificationSuite()
          // data to run the verification on
          .onData(dataset)
          // define a data quality check
          .addCheck(
            Check(CheckLevel.Error, "Review Check") 
              .hasSize(_ >= 3000000) // at least 3 million rows
              .hasMin("star_rating", _ == 1.0) // min is 1.0
              .hasMax("star_rating", _ == 5.0) // max is 5.0
              .isComplete("review_id") // should never be NULL
              .isUnique("review_id") // should not contain duplicates
              .isComplete("marketplace") // should never be NULL
              // contains only the listed values
              .isContainedIn("marketplace", Array("US", "UK", "DE", "JP", "FR"))
              .isNonNegative("year")) // should not contain negative values
          // compute metrics and verify check conditions
          .run()
        }

        // convert check results to a Spark data frame
        val resultDataFrame = checkResultsAsDataFrame(sparkSession, verificationResult)
        // convert check results to a Dynamic Frame
        val resultDynamicDataFrame = DynamicFrame(resultDataFrame, glueContext)
        // Enviar resultados a S3
        glueContext.getSinkWithFormat(connectionType = "s3", options = JsonOptions(Map("path" -> ouputDeequDir)),
            format = "parquet", transformationContext = "").writeDynamicFrame(resultDynamicDataFrame)
   }
}