test:


**For OC1 :**
1. upload a file: 
```
curl -v -X POST http://localhost:8080/oc1/upload -F "file=@/Users/sssaikia/Downloads/24f45da1feca4f579d2a3377808029e6_40483.pdf"
```
2. copy a file from one bucket to another: 
```
curl -v -X POST "http://localhost:8080/oc1/copy?sourceBucket=test-src-bucket&sourceFile=24f45da1feca4f579d2a3377808029e6_40483.pdf&destBucket=test-dst-bucket&destFile=new_24f45da1feca4f579d2a3377808029e6_40483.pdf"
```
3. download a file : 
```

```

**For OC10 :**
1. upload a file:
```
curl -v -X POST http://localhost:8080/oc10/upload -F "file=@/Users/sssaikia/Downloads/24f45da1feca4f579d2a3377808029e6_40483.pdf"
```
2. copy a file from one bucket to another:
```
curl -v -X POST "http://localhost:8080/oc10/copy?sourceBucket=test-src-bucket&sourceFile=24f45da1feca4f579d2a3377808029e6_40483.pdf&destBucket=test-dst-bucket&destFile=new_24f45da1feca4f579d2a3377808029e6_40483.pdf"
```
3. download a file : 
``` 
```
