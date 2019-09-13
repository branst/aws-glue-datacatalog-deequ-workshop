import sys
import os
from awsglue.transforms import *
from awsglue.utils import getResolvedOptions
from pyspark.context import SparkContext
from awsglue.context import GlueContext
from awsglue.job import Job
from pyspark.sql.functions import udf
from awsglue.dynamicframe import DynamicFrame
from pyspark.sql.types import StringType
import base64

# @params: [JOB_NAME]
args = getResolvedOptions(sys.argv, ['JOB_NAME'])
sc = SparkContext()
glueContext = GlueContext(sc)
spark = glueContext.spark_session
job = Job(glueContext)
job.init(args['JOB_NAME'], args)

sourceDir = 's3://amazon-reviews-pds/parquet/product_category=Electronics'
outputDir = 's3://bucket-glue-target/target/productos'

source = spark.read.load(sourceDir)
datasource0 = DynamicFrame.fromDF(source, glueContext, "source")

applymapping1 = ApplyMapping.apply(
    frame=datasource0,
    mappings=[
        ("marketplace", "string", "marketplace", "string"),
        ("customer_id", "string", "customer_id", "string"),
        ("review_id", "string", "review_id", "string"),
        ("product_title", "string", "product_title", "string"),
        ("star_rating", "integer", "star_rating", "integer"),
        ("helpful_votes", "integer", "helpful_votes", "integer"),
        ("total_votes", "integer", "total_votes", "integer"),
        ("vine", "string", "vine", "string"),
        ("year", "integer", "year", "integer")
    ], transformation_ctx="applymapping1"
)
resolvechoice2 = ResolveChoice.apply(
    frame=applymapping1, choice="make_struct", transformation_ctx="resolvechoice2")
dropnullfields3 = DropNullFields.apply(
    frame=resolvechoice2, transformation_ctx="dropnullfields3")
# convert to a Spark DataFrame...
customDF = dropnullfields3.toDF()
udf_base64 = udf(lambda x: base64.b64encode(x.encode('utf8')), StringType())
customDF = customDF.withColumn(
    "customer_id", udf_base64(customDF["customer_id"]))
customDF.show()
# Fin de UDF
customDynamicFrame = DynamicFrame.fromDF(customDF, glueContext, "customDF_df")
datasink4 = glueContext.write_dynamic_frame.from_options(frame=customDynamicFrame, connection_type="s3", connection_options={
                                                         "path": outputDir}, format="parquet", transformation_ctx="datasink4")
job.commit()
