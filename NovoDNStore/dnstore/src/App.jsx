import { BrowserRouter, Routes, Route, useLocation } from "react-router-dom";

import Home from "./pages/Home";
import Login from "./pages/Auth/Login";
import Register from "./pages/Auth/Register";
import ForgotPassword from "./pages/Auth/ForgotPassword";
import Header from "./components/Header";



function App() {
  

  return (
    
    <BrowserRouter>
      <Header/>
      <Routes>
     
        <Route path="/" element={<Home />} />

        <Route path="/login" element={<Login />} />
        <Route path="/cadastro" element={<Register />} />
        <Route path="/recuperar" element={<ForgotPassword />} />

      </Routes>
    </BrowserRouter>
  );
}

export default App;