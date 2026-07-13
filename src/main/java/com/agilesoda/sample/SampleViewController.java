package com.agilesoda.sample;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SampleViewController {
	@GetMapping("test")
	public String test() {
		return "test/Test";
	}

	@GetMapping("login")
	public String login(Model model) {
		model.addAttribute("title", "로그인");
		return "test/login";
	}
}
