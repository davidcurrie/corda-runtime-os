import ChatWrapper from '@/components/VNodeHome/ChatWrapper/ChatWrapper';
import { MessagesContextProvider } from '@/contexts/messagesContext';
import MobileChatWrapper from '@/components/VNodeHome/MobileChatWrapper/MobileChatWrapper';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import VNodeHomeViz from '@/components/Visualizations/VNodeHomeViz';
import VisualizationWrapper from '@/components/Visualizations/VisualizationWrapper';
import useAppDataContext from '@/contexts/appDataContext';
import { useEffect } from 'react';
import { useMobileMediaQuery } from '@/hooks/useMediaQueries';
import useUserContext from '@/contexts/userContext';

const VNodeHome = () => {
    const isMobile = useMobileMediaQuery();
    const { vNodes } = useAppDataContext();
    const { vNode, setVNode, holderShortId } = useUserContext();

    useEffect(() => {
        if (vNode) return;
        const myVNode = vNodes.find((vNode) => vNode.holdingIdentity.shortHash === holderShortId);
        setVNode(myVNode);
    }, [vNode]);

    return (
        <MessagesContextProvider>
            <PageContentWrapper footerEnabled={isMobile ? false : true}>
                {isMobile ? <MobileChatWrapper /> : <ChatWrapper />}
                {!isMobile && (
                    <VisualizationWrapper width={520}>
                        <VNodeHomeViz />
                    </VisualizationWrapper>
                )}
            </PageContentWrapper>
        </MessagesContextProvider>
    );
};

export default VNodeHome;