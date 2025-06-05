package com.pdf.marsk.pdfdemo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(ImageToPdfController.class)
public class ImageToPdfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testShowImageUploadPage() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/images-to-pdf"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.view().name("images-to-pdf"))
                .andExpect(MockMvcResultMatchers.model().attribute("title", "Generate PDF from Images"));
    }

    @Test
    public void testConvertImagesWithoutFiles() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.multipart("/images-to-pdf/convert"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }
}
