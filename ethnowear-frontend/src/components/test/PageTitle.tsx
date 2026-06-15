import {Typography} from "@mui/material";

type PageTitleProps = {
    title: string
}


function PageTitle(props: PageTitleProps) {
    return(
     <Typography color='primary' variant='h1'>{props.title}</Typography>
    )
}

export default PageTitle;