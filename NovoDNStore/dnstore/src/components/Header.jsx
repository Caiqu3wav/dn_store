import '../components/Header.css'
import logo from '../Images/logo.jfif'
import { Link } from "react-router-dom";

function Header() {
  return (
    <header className= "header">
        <img src= "/Images/logo.jfif" alt="logo" className="logo-img"/>
      

      
      
     <div className="right">
      <Link to="/Pesquisar">Pesquisar</Link>
    <Link to="/Login">Login</Link>
    <Link to="/carrinho">Carrinho</Link>
  </div>
    </header>
  )
}

export default Header