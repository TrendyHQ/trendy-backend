package auth0;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

import io.github.cdimascio.dotenv.Dotenv;
import software.amazon.awssdk.core.sync.RequestBody;

public class UploadFile {

    public void setProperties() {
        Dotenv dotenv = Dotenv.load();

        String keyId = dotenv.get("AWS_ACCESS_KEY_ID");
        String secretKey = dotenv.get("AWS_SECRET_ACCESS_KEY");

        System.setProperty("aws.region", "us-east-2");
        System.setProperty("aws.accessKeyId", keyId);
        System.setProperty("aws.secretAccessKey", secretKey);
    }

    public String uploadToS3(MultipartFile file) throws IOException {

        String bucketName = "trendy-assets";
        String fileName = file.getOriginalFilename();

        if (fileName != null) {
            fileName = fileName.replaceAll(" ", "_");
        }
        // Initialize S3 client
        setProperties();
        S3Client s3 = S3Client.create();

        // Upload the file
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // Return the file URL
        return "https://" + bucketName + ".s3.us-east-2.amazonaws.com/" + fileName;
    }

}
