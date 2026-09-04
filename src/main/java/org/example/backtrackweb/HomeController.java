package org.example.backtrackweb;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    @GetMapping("/")
    public String loginPage(HttpSession session) {

        if (session.getAttribute("loggedIn") != null) {
            return "redirect:/dashboard";
        }

        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session) {

        if (username.equals("admin") && password.equals("admin123")) {

            session.setAttribute("loggedIn", true);
            session.setAttribute("username", username);

            return "redirect:/dashboard";
        }

        return "redirect:/?error=true";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session) {

        if (session.getAttribute("loggedIn") == null) {
            return "redirect:/";
        }

        return "index";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {

        session.invalidate();

        return "redirect:/";
    }
}