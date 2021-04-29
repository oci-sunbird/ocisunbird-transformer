package com.samagra.transformer.application;


import com.samagra.transformer.odk.FormDownloader;
import com.samagra.transformer.odk.MenuManager;
import com.samagra.transformer.odk.ODKTransformer;
import com.samagra.transformer.odk.ServiceResponse;
import com.samagra.transformer.odk.model.Form;
import com.samagra.transformer.odk.model.FormDetails;
import com.samagra.transformer.odk.openrosa.OpenRosaAPIClient;
import com.samagra.transformer.odk.openrosa.OpenRosaHttpInterface;
import com.samagra.transformer.odk.openrosa.okhttp.OkHttpConnection;
import com.samagra.transformer.odk.openrosa.okhttp.OkHttpOpenRosaServerClientProvider;
import com.samagra.transformer.odk.persistance.FormsDao;
import com.samagra.transformer.odk.persistance.JsonDB;
import com.samagra.transformer.odk.utilities.FormListDownloader;
import com.samagra.transformer.odk.utilities.WebCredentialsUtils;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import okhttp3.OkHttpClient;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Log
@RestController
public class FormTransformerTestAPI {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    private void downloadForms() {
        //Empty the database and folder
        FormsDao dao;
        try{
            File directoryToDelete = new File("/tmp/forms2");
            FileSystemUtils.deleteRecursively(directoryToDelete);
            dao = new FormsDao(JsonDB.getInstance().getDB());
            dao.deleteFormsDatabase();
        }catch (Exception e){}

        //Create a folder /tmp/forms
        new File("/tmp/forms2").mkdirs();

        //Download fresh
        OpenRosaHttpInterface openRosaHttpInterface = new OkHttpConnection(
                new OkHttpOpenRosaServerClientProvider(new OkHttpClient()),
                null,
                "userAgent"
        );
        WebCredentialsUtils webCredentialsUtils = new WebCredentialsUtils();
        OpenRosaAPIClient openRosaAPIClient = new OpenRosaAPIClient(openRosaHttpInterface, webCredentialsUtils);
        FormListDownloader formListDownloader = new FormListDownloader(
                openRosaAPIClient,
                webCredentialsUtils);
        HashMap<String, FormDetails> formList = formListDownloader.downloadFormList(false);
        int count = 0;
        if (formList.size() > 0) {
            ArrayList<FormDetails> forms = new ArrayList<>();
            for (Map.Entry<String, FormDetails> form : formList.entrySet()) {
                forms.add(form.getValue());
                count += 1;
            }
            FormDownloader formDownloader = null;
            dao = new FormsDao(JsonDB.getInstance().getDB());
            formDownloader = new FormDownloader(dao, openRosaAPIClient);
            formDownloader.downloadForms(forms);
            List<Form> downloadedForms =  dao.getForms();
            log.info("Total downloaded forms: " + downloadedForms.size());
        }
    }

    @GetMapping("/odk/updateAll")
    public void updateForms(){
        downloadForms();
    }

    @SneakyThrows
    @GetMapping("/generate-message")
    public ServiceResponse greeting(@RequestParam(value = "previousPath", required = false) String previousPath,
                                    @RequestParam(value = "currentAnswer", required = false) String currentAnswer,
                                    @RequestParam(value = "instanceXMlPrevious", required = false) String instanceXMlPrevious,
                                    @RequestParam(value = "botFormName", required = false) String botFormName) {

        if(previousPath != null) previousPath = URLDecoder.decode(previousPath, "UTF-8");
        if(instanceXMlPrevious != null) instanceXMlPrevious = URLDecoder.decode(instanceXMlPrevious, "UTF-8");
        if(currentAnswer != null) currentAnswer = URLDecoder.decode(currentAnswer, "UTF-8");
        log.info("PreviousPath: " + previousPath);
        log.info("CurrentAnswer" +  currentAnswer);
        log.info("InstanceCurrentXML" + instanceXMlPrevious);
        log.info("botFormName" +  botFormName);
        String formPath = ODKTransformer.getFormPath(botFormName);
        ServiceResponse serviceResponse = new MenuManager(previousPath, currentAnswer, instanceXMlPrevious, formPath).start();
        System.out.println(serviceResponse.getCurrentResponseState());
        return serviceResponse;
    }
}
