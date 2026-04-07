import { BrowserRouter, Routes, Route } from "react-router-dom";
import { Provider } from "./components/ui/provider"


import Home from "./pages/Home";
import Login from "./pages/Auth/Login";
import Register from "./pages/Auth/Register";
import ForgotPassword from "./pages/Auth/ForgotPassword";
import Header from "./components/Header";



function App() {


  return (
    <Provider>
      <BrowserRouter>
        <Header />
        <Routes>
          <Route path="/" element={<Home />} />

          <Route path="/login" element={<Login />} />
          <Route path="/cadastro" element={<Register />} />
          <Route path="/recuperar" element={<ForgotPassword />} />

        </Routes>
      </BrowserRouter>
    </Provider>
  );
}

export default App;