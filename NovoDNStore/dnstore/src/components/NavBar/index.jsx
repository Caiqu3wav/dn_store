import './style.css'
import logo from '../../Images/logo.jfif'
import { Link } from "react-router-dom";
import { Box, Icon } from "@chakra-ui/react"
import { Search, ShoppingBag, UserRound } from 'lucide-react';

function NavBar() {
  return (
    <Box className="navbar">
      <Link to="/"><img src={logo} alt="logo" className="logo-img" /></Link>
      <div className="right">
        <Link className='icon' to="/Pesquisar"><Icon><Search/></Icon><span className='text-link'>Pesquisar</span></Link>
        <Link className='icon' to="/Login"><Icon><UserRound/></Icon><span className='text-link'>Login</span></Link>
        <Link className='icon' to="/carrinho"><Icon><ShoppingBag/></Icon><span className='text-link'>Carrinho</span></Link>
      </div>
    </Box>
  )
}

export default NavBar