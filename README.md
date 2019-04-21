[![](https://images.microbadger.com/badges/image/hejfelix/s3-static-web.svg)](https://microbadger.com/images/hejfelix/s3-static-web "Get your own image badge on microbadger.com")

# s3-static-web
Turn your bucket into a webpage without the lambda hassle


# Configure

The app requires a number of environment variables:

* AWS_ACCESS_KEY_ID
* AWS_SECRET_ACCESS_KEY
* AWS_REGION
* BUCKET_NAME
* *_Optional_*
  * BASIC_USER 
  * BASIC_PASS

# Run

To run the service, export all the needed variables and issue:

```bash
docker-compose up
```

