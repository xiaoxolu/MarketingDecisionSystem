package com.example.marketing.controller;

import com.example.marketing.entity.SysUser;
import com.example.marketing.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        RedirectAttributes ra) {
        try {
            SysUser user = userService.login(username, password);
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            return "redirect:/";
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           HttpSession session,
                           RedirectAttributes ra) {
        try {
            SysUser user = userService.register(username, password);
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            return "redirect:/";
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/vip")
    public String vipPage(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        SysUser user = userService.getById(userId);
        model.addAttribute("user", user);
        return "vip";
    }

    @PostMapping("/recharge")
    public String recharge(@RequestParam BigDecimal amount,
                           HttpSession session,
                           RedirectAttributes ra) {
        Long userId = (Long) session.getAttribute("userId");
        try {
            userService.recharge(userId, amount);
            ra.addFlashAttribute("msg", "充值成功！已充入 " + amount + " 元");
            ra.addFlashAttribute("msgType", "success");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("msg", e.getMessage());
            ra.addFlashAttribute("msgType", "danger");
        }
        return "redirect:/vip";
    }

    @PostMapping("/subscribe")
    public String subscribe(@RequestParam int planType,
                            HttpSession session,
                            RedirectAttributes ra) {
        Long userId = (Long) session.getAttribute("userId");
        try {
            userService.subscribe(userId, planType);
            String planName = planType == 1 ? "月度会员" : "年度会员";
            ra.addFlashAttribute("msg", "成功开通" + planName + "！");
            ra.addFlashAttribute("msgType", "success");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("msg", e.getMessage());
            ra.addFlashAttribute("msgType", "danger");
        }
        return "redirect:/vip";
    }
}
